package com.manuskript.review;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Baut den Anzeige-Puffer: gelöschter Text bleibt stehen, neuer Text steht daneben.
 */
public final class NiReviewDisplay {

    public enum SpanKind {
        DELETE,
        INSERT,
        COMMENT
    }

    public record Span(int displayStart, int displayEnd, String itemId, SpanKind kind) {
    }

    public record Result(String text, List<Span> spans) {
    }

    public record BaseHit(int start, int end, String insertChangeId) {
    }

    public static BaseHit toBaseRange(int displayStart, int displayEnd, String baseText, NiReviewDocument document) {
        int start = toBaseOffset(displayStart, baseText, document);
        int end = toBaseOffset(displayEnd, baseText, document);
        Result built = document == null ? null : build(baseText, document);
        String insertId = insertIdAt(displayStart, built);
        String insertIdEnd = insertIdAt(Math.max(displayStart, displayEnd), built);
        if (insertId == null) {
            insertId = insertIdEnd;
        }
        if (insertId != null && (insertIdEnd == null || insertId.equals(insertIdEnd))) {
            return new BaseHit(start, end, insertId);
        }
        return new BaseHit(Math.min(start, end), Math.max(start, end), null);
    }

    public static int toBaseOffset(int displayOffset, String baseText, NiReviewDocument document) {
        Result result = build(baseText, document);
        String display = result.text();
        int clamped = Math.max(0, Math.min(display.length(), displayOffset));
        int baseCursor = 0;
        int displayCursor = 0;
        String base = baseText == null ? "" : baseText;
        List<NiReviewChange> changes = new ArrayList<>();
        if (document != null) {
            changes.addAll(document.openChanges());
            changes.sort(Comparator.comparingInt(NiReviewChange::getStart).thenComparing(NiReviewChange::getId));
        }
        for (NiReviewChange change : changes) {
            int start = Math.max(0, Math.min(base.length(), change.getStart()));
            int end = Math.max(start, Math.min(base.length(), change.getEnd()));
            if (start < baseCursor) {
                continue;
            }
            int copied = start - baseCursor;
            if (clamped <= displayCursor + copied) {
                return baseCursor + (clamped - displayCursor);
            }
            displayCursor += copied;
            baseCursor = start;
            String oldText = change.getOldText();
            if (oldText.isEmpty() && end > start) {
                oldText = base.substring(start, end);
            }
            if (!change.getNewText().isEmpty() && !NiReviewChange.KIND_DELETE.equals(change.getKind())) {
                if (clamped <= displayCursor + change.getNewText().length()) {
                    return start;
                }
                displayCursor += change.getNewText().length();
            }
            if (!oldText.isEmpty() && !NiReviewChange.KIND_INSERT.equals(change.getKind())) {
                if (clamped <= displayCursor + oldText.length()) {
                    return start + (clamped - displayCursor);
                }
                displayCursor += oldText.length();
            }
            baseCursor = end;
        }
        return Math.min(base.length(), baseCursor + (clamped - displayCursor));
    }

    private static String insertIdAt(int displayOffset, Result result) {
        if (result == null) {
            return null;
        }
        for (Span span : result.spans()) {
            if (span.kind() == SpanKind.INSERT
                    && displayOffset >= span.displayStart()
                    && displayOffset <= span.displayEnd()) {
                return span.itemId();
            }
        }
        return null;
    }

    public static int toDisplayOffset(int baseOffset, String baseText, NiReviewDocument document) {
        Result result = build(baseText, document);
        List<NiReviewChange> changes = new ArrayList<>();
        if (document != null) {
            for (NiReviewChange change : document.openChanges()) {
                if (change.getStart() >= 0 && change.getEnd() >= change.getStart()) {
                    changes.add(change);
                }
            }
            changes.sort(Comparator.comparingInt(NiReviewChange::getStart).thenComparing(NiReviewChange::getId));
        }
        return mapBaseToDisplay(baseOffset, baseText == null ? "" : baseText, changes, result.text().length());
    }

    private NiReviewDisplay() {
    }

    public static Result build(String baseText, NiReviewDocument document) {
        String base = baseText == null ? "" : baseText;
        List<NiReviewChange> changes = new ArrayList<>();
        if (document != null) {
            for (NiReviewChange change : document.openChanges()) {
                if (change.getStart() >= 0 && change.getEnd() >= change.getStart()) {
                    changes.add(change);
                }
            }
        }
        changes.sort(Comparator.comparingInt(NiReviewChange::getStart).thenComparing(NiReviewChange::getId));

        StringBuilder display = new StringBuilder();
        List<Span> spans = new ArrayList<>();
        int cursor = 0;
        int index = 0;
        while (index < changes.size()) {
            NiReviewChange head = changes.get(index);
            int start = Math.max(0, Math.min(base.length(), head.getStart()));
            if (start < cursor) {
                appendInsertVisual(display, spans, head);
                appendDeleteVisual(display, spans, base, head, start, start);
                index++;
                continue;
            }
            display.append(base, cursor, start);
            int maxEnd = start;
            List<NiReviewChange> group = new ArrayList<>();
            while (index < changes.size()) {
                NiReviewChange change = changes.get(index);
                int changeStart = Math.max(0, Math.min(base.length(), change.getStart()));
                if (changeStart != start) {
                    break;
                }
                int end = Math.max(changeStart, Math.min(base.length(), change.getEnd()));
                maxEnd = Math.max(maxEnd, end);
                group.add(change);
                index++;
            }
            for (NiReviewChange change : group) {
                appendInsertVisual(display, spans, change);
            }
            for (NiReviewChange change : group) {
                int changeStart = Math.max(0, Math.min(base.length(), change.getStart()));
                int end = Math.max(changeStart, Math.min(base.length(), change.getEnd()));
                appendDeleteVisual(display, spans, base, change, changeStart, end);
            }
            cursor = maxEnd;
        }
        display.append(base, cursor, base.length());

        if (document != null) {
            for (NiReviewComment comment : document.openComments()) {
                int mapped = mapBaseToDisplay(comment.getStart(), base, changes, display.length());
                int mappedEnd = comment.isZeroWidth()
                        ? mapped
                        : mapBaseToDisplay(comment.getEnd(), base, changes, display.length());
                if (mappedEnd < mapped) {
                    mappedEnd = mapped;
                }
                if (mapped == mappedEnd && mapped < display.length()) {
                    mappedEnd = Math.min(display.length(), mapped + 1);
                }
                spans.add(new Span(mapped, mappedEnd, comment.getId(), SpanKind.COMMENT));
            }
        }
        return new Result(display.toString(), spans);
    }

    private static void appendInsertVisual(StringBuilder display, List<Span> spans, NiReviewChange change) {
        String newText = change.getNewText();
        if (!newText.isEmpty() && !NiReviewChange.KIND_DELETE.equals(change.getKind())) {
            int insStart = display.length();
            display.append(newText);
            spans.add(new Span(insStart, display.length(), change.getId(), SpanKind.INSERT));
        }
    }

    private static void appendDeleteVisual(StringBuilder display, List<Span> spans, String base,
                                          NiReviewChange change, int start, int end) {
        String oldText = change.getOldText();
        if (oldText.isEmpty() && end > start) {
            oldText = base.substring(start, end);
        }
        if (!oldText.isEmpty() && !NiReviewChange.KIND_INSERT.equals(change.getKind())) {
            int delStart = display.length();
            display.append(oldText);
            spans.add(new Span(delStart, display.length(), change.getId(), SpanKind.DELETE));
        }
    }

    private static int mapBaseToDisplay(int baseOffset, String base, List<NiReviewChange> changes, int displayLen) {
        int offset = Math.max(0, Math.min(base.length(), baseOffset));
        int display = offset;
        for (NiReviewChange change : changes) {
            if (change.getStart() >= offset) {
                break;
            }
            int start = change.getStart();
            int end = Math.max(start, change.getEnd());
            if (end <= offset) {
                int oldLen = end - start;
                if (oldLen == 0) {
                    oldLen = change.getOldText().length();
                }
                int newLen = NiReviewChange.KIND_DELETE.equals(change.getKind()) ? 0 : change.getNewText().length();
                int shownOld = (oldLen > 0 && !NiReviewChange.KIND_INSERT.equals(change.getKind())) ? oldLen : 0;
                display += shownOld + newLen - oldLen;
            }
        }
        return Math.max(0, Math.min(displayLen, display));
    }
}
