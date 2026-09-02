package com.manuskript.review;

import java.util.ArrayDeque;
import java.util.Comparator;

/**
 * Laufzeit-Sitzung: Anzeige-Puffer, Accept/Reject, Lektor-Edits gegen den Snapshot.
 */
public final class NiReviewSession {

    public enum PersistMode {
        PROJECT,
        PACKAGE
    }

    private String baseText;
    private final NiReviewDocument document;
    private final PersistMode persistMode;
    private final ArrayDeque<NiReviewDocument> undoCheckpoints = new ArrayDeque<>();
    private final ArrayDeque<NiReviewDocument> redoCheckpoints = new ArrayDeque<>();

    public NiReviewSession(String baseText, NiReviewDocument document, PersistMode persistMode) {
        this.baseText = baseText == null ? "" : baseText;
        this.document = document != null ? document : new NiReviewDocument();
        this.persistMode = persistMode == null ? PersistMode.PROJECT : persistMode;
    }

    public String baseText() {
        return baseText;
    }

    public NiReviewDocument document() {
        return document;
    }

    public PersistMode persistMode() {
        return persistMode;
    }

    public NiReviewDisplay.Result display() {
        return NiReviewDisplay.build(baseText, document);
    }

    public boolean hasOpenItems() {
        return document.hasOpenItems();
    }

    public NiReviewChange accept(String changeId) {
        NiReviewChange change = findChange(changeId);
        if (change == null || !change.isOpen() || change.getStart() < 0) {
            return null;
        }
        String before = baseText;
        baseText = NiReviewMerge.applyChange(baseText, change);
        int delta = baseText.length() - before.length();
        change.setStatus(NiReviewChange.STATUS_ACCEPTED);
        NiReviewMerge.shiftAfterApply(document, change, delta);
        document.setBaseHash(NiReviewHashes.sha256(baseText));
        return change;
    }

    public NiReviewChange reject(String changeId) {
        NiReviewChange change = findChange(changeId);
        if (change == null || !change.isOpen()) {
            return null;
        }
        change.setStatus(NiReviewChange.STATUS_REJECTED);
        return change;
    }

    public void pushUndoCheckpoint() {
        undoCheckpoints.push(NiReviewMerge.copyOf(document));
        redoCheckpoints.clear();
    }

    public boolean undoCheckpoint() {
        if (undoCheckpoints.isEmpty()) {
            return false;
        }
        redoCheckpoints.push(NiReviewMerge.copyOf(document));
        NiReviewMerge.replaceContents(document, undoCheckpoints.pop());
        return true;
    }

    public boolean redoCheckpoint() {
        if (redoCheckpoints.isEmpty()) {
            return false;
        }
        undoCheckpoints.push(NiReviewMerge.copyOf(document));
        NiReviewMerge.replaceContents(document, redoCheckpoints.pop());
        return true;
    }

    public void deleteChange(String changeId) {
        if (changeId == null) {
            return;
        }
        document.getChanges().removeIf(change -> changeId.equals(change.getId()));
    }

    public void deleteComment(String commentId) {
        if (commentId == null) {
            return;
        }
        document.getComments().removeIf(comment -> commentId.equals(comment.getId()));
    }

    public void resolveComment(String commentId) {
        NiReviewComment comment = findComment(commentId);
        if (comment != null) {
            comment.setStatus(NiReviewComment.STATUS_DONE);
        }
    }

    public NiReviewComment addComment(int baseStart, int baseEnd, String text, boolean zeroWidth) {
        NiReviewComment comment = new NiReviewComment();
        comment.setStart(Math.max(0, baseStart));
        comment.setEnd(Math.max(comment.getStart(), baseEnd));
        comment.setZeroWidth(zeroWidth || comment.getStart() == comment.getEnd());
        comment.setText(text == null ? "" : text.trim());
        comment.setPrefix(NiReviewHashes.contextBefore(baseText, comment.getStart(), 60));
        comment.setSuffix(NiReviewHashes.contextAfter(baseText, comment.getEnd(), 60));
        comment.setAuthor(NiReviewRole.reviewerName());
        comment.setCreated(NiReviewHashes.nowIso());
        document.getComments().add(comment);
        return comment;
    }

    /**
     * Lektor hat im Anzeige-Puffer getippt. Erzeugt oder erweitert eine pending-Änderung.
     */
    public NiReviewChange recordDisplayEdit(String oldDisplay, String newDisplay) {
        if (oldDisplay == null) {
            oldDisplay = "";
        }
        if (newDisplay == null) {
            newDisplay = "";
        }
        if (oldDisplay.equals(newDisplay)) {
            return null;
        }
        int start = 0;
        int min = Math.min(oldDisplay.length(), newDisplay.length());
        while (start < min && oldDisplay.charAt(start) == newDisplay.charAt(start)) {
            start++;
        }
        int oldEnd = oldDisplay.length();
        int newEnd = newDisplay.length();
        while (oldEnd > start && newEnd > start
                && oldDisplay.charAt(oldEnd - 1) == newDisplay.charAt(newEnd - 1)) {
            oldEnd--;
            newEnd--;
        }
        String removed = oldDisplay.substring(start, oldEnd);
        String inserted = newDisplay.substring(start, newEnd);
        NiReviewDisplay.BaseHit hit = NiReviewDisplay.toBaseRange(start, oldEnd, baseText, document);
        NiReviewChange extended = extendExisting(hit, start, removed, inserted);
        if (extended != null) {
            return extended;
        }
        if (inserted.isEmpty() && coversOpenDelete(hit.start(), hit.end())) {
            return findOpenDeleteCovering(hit.start(), hit.end());
        }
        NiReviewChange change = new NiReviewChange();
        change.setStart(hit.start());
        change.setEnd(Math.max(hit.start(), hit.end()));
        change.setOldText(removed.isEmpty() ? baseSlice(hit.start(), hit.end()) : removed);
        change.setNewText(inserted);
        syncKind(change, hit.start() == hit.end());
        change.setPrefix(NiReviewHashes.contextBefore(baseText, change.getStart(), 60));
        change.setSuffix(NiReviewHashes.contextAfter(baseText, change.getEnd(), 60));
        change.setAuthor(NiReviewRole.reviewerName());
        change.setCreated(NiReviewHashes.nowIso());
        document.getChanges().add(change);
        document.getChanges().sort(Comparator.comparingInt(NiReviewChange::getStart));
        return change;
    }

    /**
     * Autor tippt im Anzeige-Puffer: Änderung landet im Live-Kapitel, nicht als neue Lektor-Revision.
     */
    public int applyAuthorDisplayEdit(String oldDisplay, String newDisplay) {
        if (oldDisplay == null) {
            oldDisplay = "";
        }
        if (newDisplay == null) {
            newDisplay = "";
        }
        if (oldDisplay.equals(newDisplay)) {
            return NiReviewDisplay.toDisplayOffset(baseText.length(), baseText, document);
        }
        int start = 0;
        int min = Math.min(oldDisplay.length(), newDisplay.length());
        while (start < min && oldDisplay.charAt(start) == newDisplay.charAt(start)) {
            start++;
        }
        int oldEnd = oldDisplay.length();
        int newEnd = newDisplay.length();
        while (oldEnd > start && newEnd > start
                && oldDisplay.charAt(oldEnd - 1) == newDisplay.charAt(newEnd - 1)) {
            oldEnd--;
            newEnd--;
        }
        String inserted = newDisplay.substring(start, newEnd);
        NiReviewDisplay.BaseHit hit = NiReviewDisplay.toBaseRange(start, oldEnd, baseText, document);
        int baseStart = Math.max(0, Math.min(baseText.length(), hit.start()));
        int baseEnd = Math.max(baseStart, Math.min(baseText.length(), hit.end()));
        String previous = baseText;
        String next = previous.substring(0, baseStart) + inserted + previous.substring(baseEnd);
        noteLiveBaseEdit(previous, next);
        return NiReviewDisplay.toDisplayOffset(baseStart + inserted.length(), baseText, document);
    }

    private NiReviewChange extendExisting(NiReviewDisplay.BaseHit hit, int displayStart,
                                          String removed, String inserted) {
        if (hit.insertChangeId() != null) {
            NiReviewChange existing = findChange(hit.insertChangeId());
            if (existing != null && existing.isOpen()) {
                existing.setNewText(splice(existing.getNewText(),
                        Math.max(0, displayStart - insertDisplayStart(existing)), inserted, removed));
                syncKind(existing, false);
                if (existing.getOldText().isEmpty() && existing.getNewText().isEmpty()) {
                    document.getChanges().remove(existing);
                }
                return existing;
            }
        }
        NiReviewChange nearby = openChangeAt(hit.start());
        if (nearby == null) {
            nearby = lastOpenChange();
        }
        if (nearby == null) {
            return null;
        }
        if (!removed.isEmpty() && inserted.isEmpty()
                && NiReviewChange.KIND_DELETE.equals(nearby.getKind())
                && hit.start() <= nearby.getEnd() && hit.end() >= nearby.getStart()) {
            int from = Math.min(hit.start(), nearby.getStart());
            int to = Math.max(hit.end(), nearby.getEnd());
            nearby.setStart(from);
            nearby.setEnd(to);
            nearby.setOldText(baseSlice(from, to));
            return nearby;
        }
        if (!removed.isEmpty() && !inserted.isEmpty()
                && nearby.getStart() == hit.start()
                && (NiReviewChange.KIND_DELETE.equals(nearby.getKind())
                || NiReviewChange.KIND_REPLACE.equals(nearby.getKind()))) {
            if (nearby.getOldText().isEmpty()) {
                nearby.setOldText(removed);
            }
            nearby.setNewText(inserted);
            nearby.setEnd(Math.max(nearby.getEnd(), hit.end()));
            nearby.setKind(NiReviewChange.KIND_REPLACE);
            return nearby;
        }
        if (removed.isEmpty() && !inserted.isEmpty()) {
            if (NiReviewChange.KIND_DELETE.equals(nearby.getKind()) && hit.start() == nearby.getStart()) {
                nearby.setNewText(nearby.getNewText() + inserted);
                nearby.setKind(NiReviewChange.KIND_REPLACE);
                return nearby;
            }
            if (hit.start() == nearby.getStart() || hit.start() == nearby.getEnd()) {
                nearby.setNewText(nearby.getNewText() + inserted);
                syncKind(nearby, nearby.getOldText().isEmpty());
                return nearby;
            }
        }
        return null;
    }

    private NiReviewChange openChangeAt(int start) {
        NiReviewChange best = null;
        for (NiReviewChange change : document.openChanges()) {
            if (change.getStart() == start
                    || (change.getStart() <= start && change.getEnd() >= start)) {
                best = change;
            }
        }
        return best;
    }

    private boolean coversOpenDelete(int start, int end) {
        return findOpenDeleteCovering(start, end) != null;
    }

    private NiReviewChange findOpenDeleteCovering(int start, int end) {
        for (NiReviewChange change : document.openChanges()) {
            if ((NiReviewChange.KIND_DELETE.equals(change.getKind())
                    || NiReviewChange.KIND_REPLACE.equals(change.getKind()))
                    && change.getStart() <= start && change.getEnd() >= end && end > start) {
                return change;
            }
        }
        return null;
    }

    private NiReviewChange lastOpenChange() {
        NiReviewChange last = null;
        for (NiReviewChange change : document.openChanges()) {
            last = change;
        }
        return last;
    }

    private static void syncKind(NiReviewChange change, boolean forceInsert) {
        if (change.getNewText().isEmpty() && !change.getOldText().isEmpty()) {
            change.setKind(NiReviewChange.KIND_DELETE);
        } else if (forceInsert || change.getOldText().isEmpty()) {
            change.setKind(NiReviewChange.KIND_INSERT);
            change.setEnd(change.getStart());
            change.setOldText("");
        } else {
            change.setKind(NiReviewChange.KIND_REPLACE);
        }
    }

    public void noteLiveBaseEdit(String previousBase, String nextBase) {
        if (previousBase == null || nextBase == null || previousBase.equals(nextBase)) {
            return;
        }
        NiReviewMerge.shiftAfterLiveEdit(document, previousBase, nextBase);
        baseText = nextBase;
        document.setBaseHash(NiReviewHashes.sha256(baseText));
    }

    public NiReviewChange findChange(String id) {
        if (id == null) {
            return null;
        }
        for (NiReviewChange change : document.getChanges()) {
            if (id.equals(change.getId())) {
                return change;
            }
        }
        return null;
    }

    public NiReviewComment findComment(String id) {
        if (id == null) {
            return null;
        }
        for (NiReviewComment comment : document.getComments()) {
            if (id.equals(comment.getId())) {
                return comment;
            }
        }
        return null;
    }

    private String baseSlice(int start, int end) {
        int from = Math.max(0, Math.min(baseText.length(), start));
        int to = Math.max(from, Math.min(baseText.length(), end));
        return baseText.substring(from, to);
    }

    private int insertDisplayStart(NiReviewChange change) {
        NiReviewDisplay.Result result = display();
        for (NiReviewDisplay.Span span : result.spans()) {
            if (span.kind() == NiReviewDisplay.SpanKind.INSERT && change.getId().equals(span.itemId())) {
                return span.displayStart();
            }
        }
        return 0;
    }

    private static String splice(String original, int at, String inserted, String removed) {
        String text = original == null ? "" : original;
        int index = Math.max(0, Math.min(text.length(), at));
        int removeLen = Math.min(removed == null ? 0 : removed.length(), text.length() - index);
        return text.substring(0, index) + (inserted == null ? "" : inserted) + text.substring(index + removeLen);
    }
}
