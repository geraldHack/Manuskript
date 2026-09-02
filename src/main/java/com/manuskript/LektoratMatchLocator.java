package com.manuskript;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Findet {@link LektoratMatch#getOriginal()}-Text im Kapitel und hält Offsets synchron,
 * auch nach Bearbeitungen oder bei mehrfachen Vorkommen.
 */
public final class LektoratMatchLocator {

    private LektoratMatchLocator() {
    }

    /** Aktualisiert alle Match-Offsets in-place anhand des aktuellen Kapiteltexts. */
    public static void resolveAllInPlace(String chapterText, List<LektoratMatch> matches) {
        if (chapterText == null || matches == null || matches.isEmpty()) {
            return;
        }
        List<LektoratMatch> ordered = new ArrayList<>(matches);
        ordered.sort(Comparator.comparingInt(LektoratMatch::getOffset));
        List<int[]> occupied = new ArrayList<>();
        for (LektoratMatch match : ordered) {
            int[] span = resolveSpan(chapterText, match, occupied);
            if (span == null) {
                continue;
            }
            match.setOffset(span[0]);
            match.setLength(span[1] - span[0]);
            occupied.add(span);
        }
    }

    /**
     * @return {@code [start, end)} oder {@code null}
     */
    public static int[] resolveSpan(String chapterText, LektoratMatch match) {
        return resolveSpan(chapterText, match, List.of());
    }

    /**
     * @return {@code [start, end)} oder {@code null}
     */
    public static int[] resolveSpan(String chapterText, LektoratMatch match, List<int[]> occupiedRanges) {
        if (chapterText == null || match == null) {
            return null;
        }
        String original = match.getOriginal();
        if (original == null || original.isEmpty()) {
            return null;
        }
        int hint = match.getOffset();
        int len = match.getLength() > 0 ? match.getLength() : original.length();
        int start = hint;
        int end = start + len;
        if (start >= 0 && end <= chapterText.length()
                && chapterText.substring(start, end).equals(original)
                && !overlaps(start, end, occupiedRanges)) {
            return new int[]{start, end};
        }
        int located = locateNearest(chapterText, original, hint, occupiedRanges);
        if (located < 0) {
            return null;
        }
        return new int[]{located, located + original.length()};
    }

    /**
     * Beschreibt eine Textänderung als Prefix/Suffix-Diff.
     *
     * @param changeStart Index des ersten geänderten Zeichens
     * @param changeEndOld Exklusives Ende der geänderten Region im alten Text
     * @param delta {@code newLength - oldLength}
     */
    public record TextChange(int changeStart, int changeEndOld, int delta) {
    }

    /**
     * Ermittelt die geänderte Region zwischen zwei Textständen.
     *
     * @return {@code null} wenn beide Texte gleich sind
     */
    public static TextChange computeTextChange(String oldText, String newText) {
        if (oldText == null || newText == null || oldText.equals(newText)) {
            return null;
        }

        int changeStart = 0;
        int minLen = Math.min(oldText.length(), newText.length());
        while (changeStart < minLen && oldText.charAt(changeStart) == newText.charAt(changeStart)) {
            changeStart++;
        }
        if (changeStart >= minLen && oldText.length() == newText.length()) {
            return null;
        }

        int oldEnd = oldText.length() - 1;
        int newEnd = newText.length() - 1;
        while (oldEnd >= changeStart && newEnd >= changeStart
                && oldText.charAt(oldEnd) == newText.charAt(newEnd)) {
            oldEnd--;
            newEnd--;
        }
        int changeEndOld = oldEnd + 1;
        int delta = newText.length() - oldText.length();
        return new TextChange(changeStart, changeEndOld, delta);
    }

    /**
     * Wendet eine zuvor berechnete Textänderung auf Match-Offsets an.
     * Treffer, die die Änderungsregion überlappen, werden entfernt.
     *
     * @return {@code true} wenn mindestens ein Match entfernt oder verschoben wurde
     */
    public static boolean applyTextChange(TextChange change, List<LektoratMatch> matches) {
        if (change == null || matches == null || matches.isEmpty()) {
            return false;
        }
        int changeStart = change.changeStart();
        int changeEndOld = change.changeEndOld();
        int delta = change.delta();

        List<LektoratMatch> toRemove = new ArrayList<>();
        boolean shifted = false;
        for (LektoratMatch match : matches) {
            int matchOffset = match.getOffset();
            int matchLength = match.getLength() > 0
                    ? match.getLength()
                    : (match.getOriginal() != null ? match.getOriginal().length() : 0);
            int matchEnd = matchOffset + matchLength;
            if (matchOffset < changeEndOld && matchEnd > changeStart) {
                toRemove.add(match);
            } else if (matchOffset >= changeEndOld) {
                if (delta != 0) {
                    match.setOffset(matchOffset + delta);
                    shifted = true;
                }
            }
        }
        if (!toRemove.isEmpty()) {
            matches.removeAll(toRemove);
            return true;
        }
        return shifted;
    }

    /**
     * Verschiebt Match-Offsets nach einer Textänderung (Prefix/Suffix-Diff).
     * Treffer, die die Änderungsregion überlappen, werden entfernt.
     *
     * @return {@code true} wenn mindestens ein Match entfernt oder verschoben wurde
     */
    public static boolean shiftAfterTextChange(String oldText, String newText, List<LektoratMatch> matches) {
        return applyTextChange(computeTextChange(oldText, newText), matches);
    }

    /**
     * Nächster Treffer nach der Cursor-Position (kleinster Offset {@code > caret}).
     * Gibt es keinen mehr dahinter, den ersten Treffer im Text (Wrap).
     */
    public static LektoratMatch nextAfterCaret(List<LektoratMatch> matches, int caret) {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        LektoratMatch next = null;
        LektoratMatch first = null;
        for (LektoratMatch match : matches) {
            if (match == null) {
                continue;
            }
            int start = match.getOffset();
            if (first == null || start < first.getOffset()) {
                first = match;
            }
            if (start > caret && (next == null || start < next.getOffset())) {
                next = match;
            }
        }
        return next != null ? next : first;
    }

    /**
     * Sequentielle Suche für API-Einträge (Reihenfolge im JSON ≈ Textposition).
     */
    public static int locateSequential(String chapterText, String original, int searchFrom) {
        if (chapterText == null || original == null || original.isEmpty()) {
            return -1;
        }
        int from = Math.max(0, searchFrom);
        return chapterText.indexOf(original, from);
    }

    private static int locateNearest(String chapterText, String original, int hint, List<int[]> occupiedRanges) {
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        int from = 0;
        while (from < chapterText.length()) {
            int idx = chapterText.indexOf(original, from);
            if (idx < 0) {
                break;
            }
            int end = idx + original.length();
            if (!overlaps(idx, end, occupiedRanges)) {
                int distance = hint >= 0 ? Math.abs(idx - hint) : idx;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = idx;
                }
            }
            from = idx + 1;
        }
        return best;
    }

    private static boolean overlaps(int start, int end, List<int[]> occupiedRanges) {
        if (occupiedRanges == null || occupiedRanges.isEmpty()) {
            return false;
        }
        for (int[] range : occupiedRanges) {
            if (range == null || range.length < 2) {
                continue;
            }
            if (start < range[1] && end > range[0]) {
                return true;
            }
        }
        return false;
    }
}
