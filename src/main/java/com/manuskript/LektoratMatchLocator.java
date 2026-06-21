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
